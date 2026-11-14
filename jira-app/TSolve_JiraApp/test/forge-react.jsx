// Stand-ins for the UI Kit components, which only render inside Jira. Tests check the page's behaviour with them;
// the real look is checked in `forge tunnel`.
import React from 'react';

const Pass = ({ children }) => <div>{children}</div>;

export const Box = Pass;
export const Stack = Pass;
export const Inline = Pass;
export const ButtonGroup = Pass;
// Icons are decoration here; a labelled one still gets its accessible name.
export const Icon = ({ label }) => (label ? <span role="img" aria-label={label} /> : null);
export const xcss = (style) => style;
export const Heading =({ children }) => <h2>{children}</h2>;
export const Text = ({ children }) => <p>{children}</p>;
export const Label = ({ labelFor, children }) => <label htmlFor={labelFor}>{children}</label>;
export const ErrorMessage = ({ children }) => <div role="alert">{children}</div>;
export const List = ({ children }) => <ul>{children}</ul>;
export const ListItem = ({ children }) => <li>{children}</li>;
export const Spinner = ({ label }) => <span role="progressbar" aria-label={label} />;
export const SectionMessage = ({ appearance, title, children }) => (
  <section data-appearance={appearance}>
    {title && <h3>{title}</h3>}
    {children}
  </section>
);
export const Button = ({ children, onClick, isDisabled, appearance }) => (
  <button type="button" onClick={onClick} disabled={isDisabled} data-appearance={appearance}>
    {children}
  </button>
);
export const TextArea = ({ id, value, onChange, isDisabled, isInvalid, placeholder }) => (
  <textarea
    id={id}
    value={value}
    onChange={onChange}
    disabled={isDisabled}
    aria-invalid={isInvalid}
    placeholder={placeholder}
  />
);

export const Textfield = ({ id, value, onChange, isDisabled, placeholder }) => (
  <input id={id} value={value} onChange={onChange} disabled={isDisabled} placeholder={placeholder} />
);
export const Link = ({ href, children }) => <a href={href}>{children}</a>;
export const Lozenge = ({ children }) => <span>{children}</span>;
// The Modal opens as a dialog; its close (X) is a button named "Close dialog", which calls onClose.
export const ModalTransition = Pass;
export const Modal = ({ onClose, children }) => (
  <div role="dialog">
    <button type="button" onClick={onClose}>
      Close dialog
    </button>
    {children}
  </div>
);
export const ModalHeader = Pass;
export const ModalTitle = ({ children }) => <h1>{children}</h1>;
export const ModalBody = Pass;
export const ModalFooter = Pass;

export const useProductContext = jest.fn();

export default { render: jest.fn() };
